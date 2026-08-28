local kanRegistry = "kan"

KanNpc = {
	click = async(function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		local opts = {
			"Kan account information",
			"Beli Sertifikat Kan",
			--"Exp Envelope Packs",
			"Tas kerajinan",
			"Cosmetics",
			"Browse Mounts",
			"Random Mount Boxes",
			"Lihat Lainnya",
			"War Paint"
		}

		local choice = player:menuString(
			"Halo dan selamat datang di toko Kan! Ada yang bisa kubantu hari ini?",
			opts
		)

		if choice == "Kan account information" then
			KanNpc.getKanAccountInfo(player)
		elseif choice == "Beli Sertifikat Kan" then
			local items = {
				"kan_certificate_500",
				"kan_certificate_1000",
				"kan_certificate_1500",
				"kan_certificate_2000",
				"kan_certificate_2500",
				"kan_certificate_3000",
				"kan_certificate_3500",
				"kan_certificate_4000",
				"kan_certificate_4500",
				"kan_certificate_5000"
			}

			player:kanBuyExtend(
				"I think I can accommodate some of the things you need. What would you like?",
				items
			)
		elseif choice == "Exp Envelope Packs" then
			local packs = {
				"experience_envelope_pack_1_day",
				"experience_envelope_pack_7_days",
				"experience_envelope_pack_30_days"
			}
			local packNames = {}
			local timeLeft = 0

			for i = 1, #packs do
				table.insert(packNames, Item(packs[i]).name)
			end

			local packChoice = player:menuSeq(
				"Pilih satu dari pilihan di bawah.",
				packNames,
				{}
			)

			local expPack = Item(packs[packChoice])

			if packChoice == 1 then
				timeLeft = os.time() + 86400

				-- 24 hrs
			elseif packChoice == 2 then
				timeLeft = os.time() + 604800

				-- 7 days
			elseif packChoice == 3 then
				timeLeft = os.time() + 2592000

				-- 30 days
			end

			local confirm = player:menuSeq(
				"" .. expPack.name .. " seharga " .. Tools.formatNumber(expPack.price) .. " Kan. Kau ingin membelinya?",
				{"Ya", "Tidak"},
				{}
			)

			if confirm == 1 then
				if player.registry[kanRegistry] < expPack.price then
					player:dialogSeq(
						{
							t,
							"Sayangnya Kan-mu tidak cukup untuk menuntaskan pembelian ini."
						},
						0
					)
					return
				end

				characterLog.kanLogs(player, expPack, 1)
				KanNpc.removeKan(player, expPack.price)
				player:addItem(expPack.id, 1, 0, 0, timeLeft)
			end
		elseif choice == "Cosmetics" then
			local subChoices = {"Skins", "Equipment", "Faces", "Hair Dyes"}
			local subChoice = player:menuString(
				"Pilih satu pilihan\n\n<b>LURUHKAN\nIngat untuk meluruhkan warnamu (lewat pilihan War Paint) kalau barang hiasmu yang baru tidak tampil seperti yang kau harapkan.",
				subChoices
			)

			if subChoice == "Skins" then
				local skinChoice = player:menuString(
					"Pilih satu pilihan",
					{"Senjata", "Zirah"}
				)
			elseif subChoice == "Faces" then
				local faces = {}
				for i = 18051, 18073 do
					table.insert(faces, Item(i).yname)
				end

				local opts = {
					"Beli wajah",
					"Wajah berikutnya",
					"Wajah sebelumnya",
					"Nevermind"
				}

				local index = 1

				local str = "buff"

				while str == "buff" do
					local face = Item(faces[index])

					local tface = {graphic = face.icon, color = face.iconC}
					player.npcGraphic = tface.graphic
					player.npcColor = tface.color

					player.dialogType = 2
					player.lastClick = player.ID

					clone.equip(player, player)

					player.gfxFace = face.look
					player:updateState()

					local optsChoice = player:menuString(
						"<b>Face: " .. face.name .. "\n\nPrice: " .. Tools.formatNumber(face.price) .. " Kan\n\nApakah gaya ini sudah cocok?",
						opts
					)

					if optsChoice == "Beli wajah" then
						local confirm = player:menuString(
							"Ah, kulihat kau sudah menemukan wajah yang cocok.\n\nHarganya: " .. Tools.formatNumber(face.price) .. " Kan. Kau mau beli?",
							{"Ya, aku mau beli.", "Tidak, lupakan saja."}
						)

						if confirm == "Ya, aku mau beli." then
							if player.registry[kanRegistry] < face.price then
								player:dialogSeq(
									{
										t,
										"Sayangnya Kan-mu tidak cukup untuk menuntaskan pembelian ini."
									},
									0
								)
								return
							end

							characterLog.kanLogs(player, face, 1)
							KanNpc.removeKan(player, face.price)
							player.face = face.look
							player:updateState()

							return
						end
					elseif optsChoice == "Wajah berikutnya" then
						index = index + 1
						if index > #faces then
							index = #faces
						end
						player.gfxFace = face.look
					elseif optsChoice == "Wajah sebelumnya" then
						index = index - 1
						if index < 1 then
							index = 1
						end
						player.gfxFace = face.look
					elseif optsChoice == "Nevermind" then
						player.state = 0
						clone.wipe(player)
						return
					end
				end
			elseif subChoice == "Hair Dyes" then
				local items = {}
				for i = 17477, 17543 do
					table.insert(items, Item(i).yname)
				end

				player:kanBuyExtend(
					"I think I can accommodate some of the things you need. What would you like?",
					items
				)
			elseif subChoice == "Equipment" then
				local slots = {
					"Face Accessories",
					"Crown",
					"Mantle",
					"Coat",
					"Necklace",
					"Shoes",
					"Beard"
				}
				local slotChoice = player:menuString(
					"Pilih satu slot perlengkapan",
					slots
				)

				if slotChoice == "Face Accessories" then
					local items = {}

					-- shades
					for i = 17231, 17248 do
						table.insert(items, Item(i).yname)
					end

					for i = 17687, 17705 do
						table.insert(items, Item(i).yname)
					end

					player:kanBuyExtend(
						"I think I can accommodate some of the things you need. What would you like?",
						items
					)
				end

				if slotChoice == "Crown" then
					local items = {
						"blue_ogre_head",
						"blood_ogre_head",
						"green_ogre_head",
						"blue_ogress_head",
						"blood_ogress_head",
						"green_ogress_head"
					}

					-- add recess lids
					for i = 17126, 17136 do
						table.insert(items, Item(i).yname)
					end

					-- add turbans
					for i = 17707, 17716 do
						table.insert(items, Item(i).yname)
					end

					-- end add turbans

					for i = 17359, 17381 do
						table.insert(items, Item(i).yname)
					end

					for i = 17382, 17403 do
						table.insert(items, Item(i).yname)
					end

					for i = 17404, 17413 do
						table.insert(items, Item(i).yname)
					end

					for i = 17414, 17420 do
						table.insert(items, Item(i).yname)
					end

					for i = 17421, 17426 do
						table.insert(items, Item(i).yname)
					end

					for i = 17682, 17686 do
						table.insert(items, Item(i).yname)
					end

					player:kanBuyExtend(
						"I think I can accommodate some of the things you need. What would you like?",
						items
					)
				end

				if slotChoice == "Mantle" then
					local items = {
						"random_faerie_wings_box",
						"green_faerie_wings",
						"blue_faerie_wings",
						"golden_faerie_wings",
						"platinum_faerie_wings",
						"red_faerie_wings",
						"green_sprite_wings",
						"blue_sprite_wings",
						"teal_sprite_wings",
						"grey_sprite_wings",
						"silver_sprite_wings",
						"golden_sprite_wings",
						"red_sprite_wings",
						"pink_sprite_wings",
						"purple_sprite_wings",
						"mulberry_sprite_wings",
						"fuschia_sprite_wings"
					}

					--Angel wings
					for i = 17137, 17161 do
						table.insert(items, Item(i).yname)
					end

					-- Mantiyas
					for i = 17162, 17194 do
						table.insert(items, Item(i).yname)
					end

					-- Great butterfly wings (these only had the 1 single color on NTK, we have 13)
					for i = 17195, 17207 do
						table.insert(items, Item(i).yname)
					end

					table.insert(items, "lei_effect")

					player:kanBuyExtend(
						"I think I can accommodate some of the things you need. What would you like?",
						items
					)
				end

				if slotChoice == "Coat" then
					local items = {
						"scale_mail_coat",
						"war_platemail_coat",
						"scale_dress_coat",
						"war_dress_coat",
						"waistcoat_coat",
						"blouse_coat",
						"armor_coat",
						"armor_dress_coat",
						"garb_coat",
						"dress_coat",
						"skirt_coat",
						"clothes_coat",
						"robes_coat",
						"gown_coat",
						"mantle_coat",
						"drapery_coat",
						"heavy_plate_coat",
						"blue_ogre_body",
						"blood_ogre_body",
						"green_ogre_body",
						"blue_ogress_body",
						"blood_ogress_body",
						"green_ogress_body"
					}

					-- add recess outfits
					for i = 17115, 17125 do
						table.insert(items, Item(i).yname)
					end

					-- ghost coats
					for i = 17208, 17230 do
						table.insert(items, Item(i).yname)
					end

					-- kimonos/hamakas/
					for i = 17249, 17301 do
						table.insert(items, Item(i).yname)
					end

					-- Snazzy skirt/suits
					for i = 17302, 17336 do
						table.insert(items, Item(i).yname)
					end

					-- great cloaks (NTK had only one offering: embroidered jade great cloak, we have much more)
					for i = 17337, 17347 do
						table.insert(items, Item(i).yname)
					end

					-- soul serpent
					for i = 17348, 17357 do
						table.insert(items, Item(i).yname)
					end

					-- ball gown
					for i = 17648, 17672 do
						table.insert(items, Item(i).yname)
					end

					-- princely jacket
					for i = 17673, 17681 do
						table.insert(items, Item(i).yname)
					end

					-- pharoh style
					for i = 17717, 17722 do
						table.insert(items, Item(i).yname)
					end

					player:kanBuyExtend(
						"I think I can accommodate some of the things you need. What would you like?",
						items
					)
				end
				if slotChoice == "Necklace" then
					local items = {}
					for i = 17427, 17444 do
						table.insert(items, Item(i).yname)
					end

					player:kanBuyExtend(
						"I think I can accommodate some of the things you need. What would you like?",
						items
					)
				end
				if slotChoice == "Shoes" then
					local items = {}
					for i = 17445, 17465 do
						table.insert(items, Item(i).yname)
					end
					for i = 17466, 17476 do
						table.insert(items, Item(i).yname)
					end

					player:kanBuyExtend(
						"I think I can accommodate some of the things you need. What would you like?",
						items
					)
				end

				if slotChoice == "Beard" then
					local items = {}
					for i = 17544, 17647 do
						table.insert(items, Item(i).yname)
					end

					player:kanBuyExtend(
						"I think I can accommodate some of the things you need. What would you like?",
						items
					)
				end
			end
		elseif choice == "Browse Mounts" then
			local mounts = {
				"horse_mount",
				"white_horse_mount",
				"yellow_horse_mount"
			}
			local opts = {
				"Beli tunggangan",
				"Tunggangan berikutnya",
				"Tunggangan sebelumnya",
				"Nevermind"
			}

			local index = 1

			local str = "buff"

			while str == "buff" do
				local mount = Item(mounts[index])

				local tmount = {graphic = mount.icon, color = mount.iconC}
				player.npcGraphic = tmount.graphic
				player.npcColor = tmount.color

				player.state = 3
				player.disguise = mount.look
				player:updateState()

				local optsChoice = player:menuString(
					"<b>Mount: " .. mount.name .. "\n\nPrice: " .. Tools.formatNumber(mount.price) .. " Kan\n\nApakah gaya ini sudah cocok?",
					opts
				)

				if optsChoice == "Beli tunggangan" then
					local confirm = player:menuString(
						"Ah, kulihat kau sudah menemukan tunggangan yang cocok.\n\nHarganya: " .. Tools.formatNumber(mount.price) .. " Kan. Kau mau beli?",
						{"Ya, aku mau beli.", "Tidak, lupakan saja."}
					)

					if confirm == "Ya, aku mau beli." then
						if player.registry[kanRegistry] < mount.price then
							player:dialogSeq(
								{
									t,
									"Sayangnya Kan-mu tidak cukup untuk menuntaskan pembelian ini."
								},
								0
							)
							return
						end

						characterLog.kanLogs(player, mount, 1)
						KanNpc.removeKan(player, mount.price)
						player:addItem(mount.id, 1)

						return
					end
				elseif optsChoice == "Tunggangan berikutnya" then
					index = index + 1
					if index > #mounts then
						index = #mounts
					end
				elseif optsChoice == "Tunggangan sebelumnya" then
					index = index - 1
					if index < 1 then
						index = 1
					end
				elseif optsChoice == "Nevermind" then
					player.state = 0
					clone.wipe(player)
					return
				end
			end
		elseif choice == "Tas kerajinan" then
			local items = {
				"empty_ore_bag",
				"empty_metal_bag",
				"empty_amber_bag",
				"empty_wool_bag",
				"empty_ginko_bag",
				"empty_cloth_bag",
				"empty_food_bag",
				"empty_fish_bag",
				"empty_farmers_bag"
			}
			player:kanBuyExtend(
				"I think I can accommodate some of the things you need. What would you like?",
				items
			)
		elseif choice == "Lihat Lainnya" then
			local items = {
				"item_protection_i",
				"item_protection_ii",
				"item_protection_iii",
				"item_protection_v",
				"cleanse_alignment",
				"name_change_voucher",
				"crafting_stone",
				"inventory_space",
				"skill_assessment"
			}
			player:kanBuyExtend(
				"I think I can accommodate some of the things you need. What would you like?",
				items
			)
		elseif choice == "Random Mount Boxes" then
			local commonMounts, rareMounts, ultraRareMounts = {}, {}, {}
			local mounts = {}

			local items = {"random_mount_i", "random_mount_ii"}

			local itemNames = {}
			local rarityChoices = {
				"Common mounts",
				"Rare mounts",
				"Ultra Rare mounts"
			}

			for i = 1, #items do
				table.insert(itemNames, Item(items[i]).name)
			end

			local choice = player:menuSeq("Pilih satu kotak.", itemNames, {})

			local subChoice = player:menuSeq(
				"Pilih apa yang ingin kau lakukan.",
				{"Beli kotak", "Browse possible outcomes."},
				{}
			)

			if subChoice == 1 then
				-- buy
				local price = Item(items[choice]).price
				local confirm = player:menuSeq(
					"" .. Item(items[choice]).name .. " kotak seharga " .. Tools.formatNumber(price) .. " Kan. Kau ingin membelinya?",
					{"Ya", "Tidak"},
					{}
				)

				if confirm == 1 then
					if player.registry[kanRegistry] < price then
						player:dialogSeq(
							{
								t,
								"Maaf, Kan-mu tidak cukup untuk pembelian ini."
							},
							0
						)
						return
					end

					characterLog.kanLogs(player, Item(items[choice]), 1)
					KanNpc.removeKan(player, price)
					player:addItem(Item(items[choice]).yname, 1)

					player:dialogSeq(
						{
							t,
							"Ini kotakmu. Semoga beruntung\n\nTerima kasih sudah terus mendukung server kami."
						},
						0
					)

					return
				end
			elseif subChoice == 2 then
				--browse

				local rarityChoice = player:menuSeq(
					"Pilih satu tingkat kelangkaan.",
					rarityChoices,
					{}
				)

				local func = assert(loadstring("return " .. items[choice] .. ".mounts"))()

				if (func ~= nil) then
					commonMounts, rareMounts, ultraRareMounts = func()
				end

				if rarityChoice == 1 then
					-- Common
					mounts = commonMounts
				elseif rarityChoice == 2 then
					-- Rare
					mounts = rareMounts
				elseif rarityChoice == 3 then
					-- Ultra rare
					mounts = ultraRareMounts
				end

				for i = 1, #mounts do
					local mount = Item(mounts[i])
					local tmount = {graphic = mount.icon, color = mount.iconC}

					player:dialogSeq({tmount, mount.name}, 1)
				end
			end
		elseif choice == "War Paint" then
			general_npc_funcs.warPaint(player, npc)
		end
	end),

	getKanAccountInfo = function(player)
		local donate = player:menuSeq(
			"Saldo Kan: " .. Tools.formatNumber(player.registry[kanRegistry]) .. "\n\nKau ingin membeli Kan lagi?",
			{"Ya", "Tidak"},
			{}
		)

		if donate == 1 then
			local kanAmount = 1000
			local kanPrice = 3000000

			local confirmDonate = player:menuSeq(
				"Para dewa menuntut upeti yang besar sebagai penukar Kan. Kau yakin?",
				{"Beli " .. Tools.formatNumber(kanAmount) .. " Kan seharga " .. Tools.formatNumber(kanPrice) .. " keping", "Mungkin lain kali"},
				{}
			)

			if (confirmDonate ~= 1) then
				return
			end

			if (player.money < kanPrice) then
				player:dialogSeq({"Membayar apa yang kau inginkan adalah tanda pengabdian. Kembalilah kalau syaratnya sudah kau penuhi."}, 0)
				return
			end

			player.money = player.money - kanPrice
			KanNpc.addKan(player, kanAmount)
			player:sendMinitext(Tools.formatNumber(kanAmount) .. " Kan telah ditambahkan ke akunmu.")
			player:forceSave()
		end
	end,

	addKan = function(player, amount)
		player.registry[kanRegistry] = player.registry[kanRegistry] + amount
		player:forceSave()
	end,

	removeKan = function(player, amount)
		player.registry[kanRegistry] = player.registry[kanRegistry] - amount
		player:forceSave()
	end
}
