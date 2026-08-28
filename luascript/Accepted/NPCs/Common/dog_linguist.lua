DogLinguistNpc = {
	onSayClick = async(function(player, npc)
		local speech = string.lower(player.speech)

		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		if player:hasLegend("dog_linguist") then
			if speech == "rahasia" then
				Tools.checkKarma(player)

				if (npc.name == "Mutt" and (player.class == 3 or player.class == 8)) then
					-- mage dog    mage or ju jak
					-- level 70 spell: Fissure    Level 99 spell: lava surge
					if player.quest["dog_linguist_subpath"] == 0 then
						local choice = player:menuSeq(
							"Memang banyak yang bisa kuajarkan kepadamu, tetapi lebih dulu kau harus berjanji TIDAK AKAN PERNAH bergabung dengan subjalur. Kau berjanji?",
							{
								"Ya, subjalur tidak berarti apa-apa bagiku.",
								"Belum, aku belum memilih takdirku."
							},
							{}
						)
						if choice == 1 then
							player.quest["dog_linguist_subpath"] = 1
						else
							return
						end
					end
					if (player.level >= 60 and player.quest["dog_linguist"] == 1) then
						if (player:hasItem("amber", 1) == true and player:hasItem("amethyst", 1) == true and player:hasItem(
							"quartz",
							1
						) == true and player:hasItem("topaz", 1) == true) then
							player:removeItem("amber", 1)
							player:removeItem("amethyst", 1)
							player:removeItem("quartz", 1)
							player:removeItem("topaz", 1)
							player:addSpell("fissure")
							player:sendMinitext("Pikiranmu meluas saat kau mempelajari Fissure")
							player.quest["dog_linguist"] = 2
							return
						end

						player:dialogSeq(
							{
								t,
								"Ah ya, kurasa aku bisa membantumu. Bawakan aku (1) Amber, (1) Amethyst, (1) Quartz, dan (1) Topaz, lalu akan kuajari kau.",
								"Kembalilah kalau yang kuminta sudah kau dapatkan."
							},
							0
						)

						return
					elseif (player.level >= 80 and player.quest["dog_linguist"] == 2) then
						if (player:killCount("golden_lobster") >= 1 and
							player:hasItem("star_staff", 1) == true and
							player:hasItem("tao_stone", 1) == true) then
							
							player:removeItem("star_staff", 1)
							player:removeItem("tao_stone", 1)
							player:addSpell("lava_surge")
							player:sendMinitext("Pikiranmu meluas saat kau mempelajari Lava Surge")
							player.quest["dog_linguist"] = 3

							return
						end

						player:dialogSeq(
							{
								t,
								"Ah ya, kurasa aku bisa membantumu. Bunuh Lobster paling berharga lalu bawakan aku Star-staff dan Tao stone, dan akan kuajari kau.",
								"Kembalilah kalau yang kuminta sudah kau kerjakan."
							},
							0
						)

						return
					elseif player.quest["dog_linguist"] == 3 then
						player:dialog("Semua yang bisa kuajarkan sudah kuajarkan.", {})
					else
						player:dialog(
							"Temui aku lagi kalau kau sudah memperoleh sedikit pencerahan.",
							{}
						)
						return
					end
				elseif npc.name == "Jindo dog" and (player.class == 2 or player.class == 7) then
					-- rogue dog  rogue or baekho

					-- level 70 spell: spot traps    Level 99 spell: Serpent's Fury
					if player.quest["dog_linguist_subpath"] == 0 then
						local choice = player:menuSeq(
							"Memang banyak yang bisa kuajarkan kepadamu, tetapi lebih dulu kau harus berjanji TIDAK AKAN PERNAH bergabung dengan subjalur. Kau berjanji?",
							{
								"Ya, subjalur tidak berarti apa-apa bagiku.",
								"Belum, aku belum memilih takdirku."
							},
							{}
						)
						if choice == 1 then
							player.quest["dog_linguist_subpath"] = 1
						else
							return
						end
					end
					if (player.level >= 60 and player.quest["dog_linguist"] == 1) then
						if player:killCount("trapdoor_spider") >= 3 then
							player:addSpell("spot_traps")
							player:sendMinitext("Pikiranmu meluas saat kau mempelajari Spot Traps")
							player.quest["dog_linguist"] = 2
							return
						end

						player:flushKills("trapdoor_spider")
						player:dialogSeq(
							{
								t,
								"Ah ya, kurasa aku bisa membantumu. Bunuh tiga (3) Trapdoor Spider (Ambushing Spider di Kugnae Spider Cave) dan akan kuajari kau.",
								"Kembalilah kalau yang kuminta sudah kau dapatkan."
							},
							0
						)

						return
					elseif (player.level >= 80 and player.quest["dog_linguist"] == 2) then
						if (player:killCount("golden_lobster") >= 1 and player:hasItem("whisper_bracelet", 1) == true) then
							player:removeItem("whisper_bracelet", 1)
							player:addSpell("serpents_fury")
							player:sendMinitext("Pikiranmu meluas saat kau mempelajari Serpent's Fury")
							player.quest["dog_linguist"] = 3

							return
						end

						player:flushKills("golden_lobster")
						player:dialogSeq(
							{
								t,
								"Ah ya, kurasa aku bisa membantumu. Bunuh Golden Lobster, lalu bawakan aku barang berikut:\n\n(1) Whisper bracelet",
								"Kembalilah kalau yang kuminta sudah kau kerjakan."
							},
							0
						)

						return
					elseif player.quest["dog_linguist"] == 3 then
						player:dialog("Semua yang bisa kuajarkan sudah kuajarkan.", {})
					else
						player:dialog(
							"Temui aku lagi kalau kau sudah memperoleh sedikit pencerahan.",
							{}
						)
						return
					end
				elseif npc.name == "Hunting dog" and (player.class == 1 or player.class == 6) then
					-- warrior dog  warrior or chung ryong

					-- level 70 spell: Greater Blessing    Level 99 spell: Spirit Fury
					if player.quest["dog_linguist_subpath"] == 0 then
						local choice = player:menuSeq(
							"Memang banyak yang bisa kuajarkan kepadamu, tetapi lebih dulu kau harus berjanji TIDAK AKAN PERNAH bergabung dengan subjalur. Kau berjanji?",
							{
								"Ya, subjalur tidak berarti apa-apa bagiku.",
								"Belum, aku belum memilih takdirku."
							},
							{}
						)
						if choice == 1 then
							player.quest["dog_linguist_subpath"] = 1
						else
							return
						end
					end
					if (player.level >= 60 and player.quest["dog_linguist"] == 1) then
						if player:killCount("trapdoor_spider") >= 3 then
							player:addSpell("greater_blessing")
							player:sendMinitext("Pikiranmu meluas saat kau mempelajari Greater Blessing")
							player.quest["dog_linguist"] = 2
							return
						end

						player:flushKills("trapdoor_spider")
						player:dialogSeq(
							{
								t,
								"Ah ya, kurasa aku bisa membantumu. Bunuh tiga (3) Trapdoor Spider (Ambushing Spider di Kugnae Spider Cave) dan akan kuajari kau.",
								"Kembalilah kalau yang kuminta sudah kau dapatkan."
							},
							0
						)

						return
					elseif (player.level >= 80 and player.quest["dog_linguist"] == 2) then
						if (player:killCount("golden_lobster") >= 1 and player:hasItem("titanium_glove", 1) == true and player.money >= 10000) then
							player:removeItem("titanium_glove", 1)
							player:removeGold(10000)
							player:addSpell("spirit_fury")
							player.quest["dog_linguist"] = 3
							player:sendMinitext("Pikiranmu meluas saat kau mempelajari Spirit Fury")

							return
						end

						player:flushKills("golden_lobster")
						player:dialogSeq(
							{
								t,
								"Ah ya, kurasa aku bisa membantumu. Bunuh satu Golden Lobster lalu bawakan aku barang berikut:\n\n(1) Titanium glove\n10.000 emas",
								"Kembalilah kalau yang kuminta sudah kau kerjakan."
							},
							0
						)

						return
					elseif player.quest["dog_linguist"] == 3 then
						player:dialog("Semua yang bisa kuajarkan sudah kuajarkan.", {})
					else
						player:dialog(
							"Temui aku lagi kalau kau sudah memperoleh sedikit pencerahan.",
							{}
						)
						return
					end
				elseif npc.name == "Spotted dog" and (player.class == 4 or player.class == 9) then
					-- poet dog  poet or hyun moo

					-- level 70 spell: Survive    Level 99 spell: Fascinate
					if player.quest["dog_linguist_subpath"] == 0 then
						local choice = player:menuSeq(
							"Memang banyak yang bisa kuajarkan kepadamu, tetapi lebih dulu kau harus berjanji TIDAK AKAN PERNAH bergabung dengan subjalur. Kau berjanji?",
							{
								"Ya, subjalur tidak berarti apa-apa bagiku.",
								"Belum, aku belum memilih takdirku."
							},
							{}
						)
						if choice == 1 then
							player.quest["dog_linguist_subpath"] = 1
						else
							return
						end
					end
					if (player.level >= 60 and player.quest["dog_linguist"] == 1) then
						if (player:hasItem("mountain_ginseng", 10) == true and player:hasItem(
							"pearl_charm",
							1
						) == true) then
							player:removeItem("mountain_ginseng", 10)
							player:removeItem("pearl_charm", 1)
							player:addSpell("survive")
							player:sendMinitext("Pikiranmu meluas saat kau mempelajari Survive")
							player.quest["dog_linguist"] = 2
							return
						end

						player:dialogSeq(
							{
								t,
								"Ah ya, kurasa aku bisa membantumu. Bawakan aku (10) Mountain ginseng dan (1) Pearl charm, lalu akan kuajari kau.",
								"Kembalilah kalau yang kuminta sudah kau dapatkan."
							},
							0
						)

						return
					elseif (player.level >= 80 and player.quest["dog_linguist"] == 2) then
						if (player:killCount("golden_lobster") >= 1 and player:hasItem("titanium_lance", 1) == true and player:hasItem(
							"sen_glove",
							1
						) == true) then
							player:removeItem("titanium_lance", 1)
							player:removeItem("sen_glove", 1)
							player:addSpell("fascinate")
							player:sendMinitext("Pikiranmu meluas saat kau mempelajari Fascinate")
							player.quest["dog_linguist"] = 3

							return
						end

						player:flushKills("golden_lobster")
						player:dialogSeq(
							{
								t,
								"Ah ya, kurasa aku bisa membantumu. Bunuh Golden Lobster, lalu bawakan aku barang berikut:\n\n(1) Titanium lance\n(1) Sen glove",
								"Kembalilah kalau yang kuminta sudah kau kerjakan."
							},
							0
						)

						return
					elseif player.quest["dog_linguist"] == 3 then
						player:dialog("Semua yang bisa kuajarkan sudah kuajarkan.", {})
					else
						player:dialog(
							"Temui aku lagi kalau kau sudah memperoleh sedikit pencerahan.",
							{}
						)
						return
					end
				end
			elseif speech == "sucikan" then
				Tools.checkKarma(player)

				--if player.quest["dog_linguist"] < 2 or player.quest["dog_linguist_subpath"] == 1 then player:dialogSeq({t,"I cannot help you until you have helped us."},0) return end

				if (npc.name == "Mutt" and (player.class == 3 or player.class == 8)) or (npc.name == "Jindo dog" and (player.class == 2 or player.class == 7)) or (npc.name == "Hunting dog" and (player.class == 1 or player.class == 6)) or (npc.name == "Spotted dog" and (player.class == 4 or player.class == 9)) then
					if player.quest["dog_linguist"] > 1 or player.quest["dog_linguist_subpath"] == 1 then
						DogLinguistNpc.cleanse(player, npc)
					end
				end
			end

			return

			--else player:dialog("I have nothing to teach your kind.",{})
		end

		if npc.name == "Mutt" then
			if speech == "halo!" then
				npc:talk(0, npc.name .. ": Bark!")
			elseif speech == "bark!" then
				player.quest["dog_linguist"] = 1
			end
		end

		if npc.name == "Jindo dog" then
			if player.quest["dog_linguist"] < 1 then
				return
			end
			if speech == "bark!" then
				npc:talk(0, npc.name .. ": Woof!")
			elseif speech == "woof!" then
				player.quest["dog_linguist"] = 2
			end
		end

		if npc.name == "Hunting dog" then
			if player.quest["dog_linguist"] < 2 then
				return
			end
			if speech == "woof!" then
				npc:talk(0, npc.name .. ": Grrowl!")
			elseif speech == "grrowl!" then
				player.quest["dog_linguist"] = 3
			end
		end

		if npc.name == "Spotted dog" then
			if player.quest["dog_linguist"] < 3 then
				return
			end
			if speech == "grrowl!" then
				--npc:talk(0,npc.name..": Grrowl!")
				player.quest["dog_linguist"] = 1

				-- sets player at beginning quest stage to learn spell
				if not player:hasLegend("dog_linguist") then
					player:addLegend(
						"Dog linguist (" .. curT() .. ")",
						"dog_linguist",
						3,
						128
					)
					player:addKarma(1.0)
				end
				player:dialog(
					"Kau merasa sekarang bisa bercakap sederhana dalam bahasa mereka.",
					{}
				)
			end
		end
	end),

	cleanse = function(player, npc)
		local t = {
			graphic = convertGraphic(npc.look, "monster"),
			color = npc.lookColor
		}
		player.npcGraphic = t.graphic
		player.npcColor = t.color
		player.dialogType = 0
		player.lastClick = npc.ID

		--[[ Not sure why this is here? Dog linguist won't change the persons class ever.
	if player.class == 1 or player.class == 2 or player.class == 3 or player.class == 4 then
		player:dialogSeq({t,"You are already cleansed."},0)
	return
	end
	]]
		--

		local confirm = player:menuSeq(
			"Kau ingin mengosongkan pikiranmu, membuka kemungkinan salah satu subjalur? Itu akan memakan banyak karma.",
			{"Aku ingin bergabung dengan subjalur pemain.", "Nevermind."},
			{}
		)

		if confirm == 1 then
			local dogSpells = {
				"fissure",
				"lava_surge",
				"serpents_fury",
				"greater_blessing",
				"spirit_fury",
				"fascinate",
				"survive"
			}
			local aclassSpells = player:getAllClassSpells(player.class)
			local classSpells = {}

			--for i = 1, #aclassSpells do if i % 2 == 0 then table.insert(classSpells, aclassSpells[i]) end end -- VERIFIED does not remove all spells
			--for i = 1, #classSpells do player:removeSpell(classSpells[i]) end -- VERIFIED DOES NOT REMOVE ALL SPELLS
			for i = 1, #dogSpells do
				player:removeSpell(dogSpells[i])
			end

			-- TESTED AND VERIFIED in tk that it takes your npc spells
			player:removeSpell("hyun_moo_revival")
			player:removeSpell("chung_ryongs_rage")
			player:removeSpell("ju_jak_evocation")
			player:removeSpell("baekhos_cunning")

			-- ONLY REMOVES THE SPELLS IN TK

			--player:removeLegendbyName("dog_linguist") DOES NOT remove the legend mark in tk
			player:removeKarma(4.0)
			player.quest["dog_linguist"] = 1
			player.quest["dog_linguist_subpath"] = 0
			player:updatePath(player.baseClass, player.mark)

			player:dialogSeq({t, "Sudah selesai."}, 0)
		end
	end,

	move = function(npc, owner)
		npc_ai.moveInPlace(npc, owner)
	end
}
